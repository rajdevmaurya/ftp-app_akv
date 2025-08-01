# Azure VM Deployment Guide for FTP Application with Key Vault

This guide provides step-by-step instructions to deploy the Spring Boot FTP application on Azure VM with Key Vault integration.

## Prerequisites

- Azure CLI installed and configured
- Azure subscription with appropriate permissions
- SSH key pair for VM access

## Step 1: Azure CLI Login and Setup

```bash
# Login to Azure
az login

# Set your subscription (replace with your subscription ID)
az account set --subscription "your-subscription-id"

# Set variables for the deployment
export RESOURCE_GROUP="rg-echohealthcare-ftp"
export LOCATION="East US"
export VM_NAME="vm-ftp-app"
export KEYVAULT_NAME="echohelthcate-akv"
export VM_SIZE="Standard_B2s"
export ADMIN_USERNAME="azureuser"
export SSH_KEY_PATH="~/.ssh/id_rsa.pub"
```

## Step 2: Create Resource Group

```bash
# Create resource group
az group create \
  --name $RESOURCE_GROUP \
  --location "$LOCATION"

echo "Resource group created: $RESOURCE_GROUP"
```
az keyvault create \
--name "$KEYVAULT_NAME" \
--resource-group "$RESOURCE_GROUP" \
--location "$LOCATION"

## Step 3: Create Azure Key Vault

```bash
# Create Key Vault
az keyvault create \
  --name $KEYVAULT_NAME \
  --resource-group $RESOURCE_GROUP \
  --location "$LOCATION" \
  --enabled-for-vm-deployment true \
  --enabled-for-template-deployment true

echo "Key Vault created: $KEYVAULT_NAME"

# Get Key Vault URI
KEYVAULT_URI=$(az keyvault show --name $KEYVAULT_NAME --resource-group $RESOURCE_GROUP --query "properties.vaultUri" -o tsv)
echo "Key Vault URI: $KEYVAULT_URI"
```
###
az ad signed-in-user show --query id -o tsv
export USER_OBJECT_ID=$(az ad signed-in-user show --query id -o tsv)

az role assignment create \
--assignee $USER_OBJECT_ID \
--role "Key Vault Secrets Officer" \
--scope $KEYVAULT_ID


## Step 4: Add Secrets to Key Vault

```bash
# Add application secrets
az keyvault secret set --vault-name $KEYVAULT_NAME --name "ftp-username" --value "your-ftp-username"
az keyvault secret set --vault-name $KEYVAULT_NAME --name "ftp-password" --value "your-ftp-password"
az keyvault secret set --vault-name $KEYVAULT_NAME --name "ftp-server" --value "ftp.example.com"
az keyvault secret set --vault-name $KEYVAULT_NAME --name "database-connection-string" --value "jdbc:mysql://localhost:3306/ftpdb?user=dbuser&password=dbpass"
az keyvault secret set --vault-name $KEYVAULT_NAME --name "api-key" --value "your-api-key-here"

# Additional secrets for testing
az keyvault secret set --vault-name $KEYVAULT_NAME --name "email-password" --value "email-secret-password"
az keyvault secret set --vault-name $KEYVAULT_NAME --name "jwt-secret" --value "your-jwt-secret-key"
az keyvault secret set --vault-name $KEYVAULT_NAME --name "azure-storage-key" --value "your-storage-account-key"

echo "Secrets added to Key Vault"
```

## Step 5: Create Virtual Machine

```bash
# Create VM with system-assigned managed identity
az vm create \
  --resource-group $RESOURCE_GROUP \
  --name $VM_NAME \
  --image "Ubuntu2204" \
  --size $VM_SIZE \
  --admin-username $ADMIN_USERNAME \
  --ssh-key-values $SSH_KEY_PATH \
  --assign-identity \
  --public-ip-sku Standard \
  --storage-sku Standard_LRS

echo "VM created: $VM_NAME"

# Get VM's managed identity principal ID
PRINCIPAL_ID=$(az vm identity show --resource-group $RESOURCE_GROUP --name $VM_NAME --query "principalId" -o tsv)
echo "VM Managed Identity Principal ID: $PRINCIPAL_ID"

# Get VM public IP
VM_PUBLIC_IP=$(az vm show --resource-group $RESOURCE_GROUP --name $VM_NAME --show-details --query "publicIps" -o tsv)
echo "VM Public IP: $VM_PUBLIC_IP"
```

## Step 6: Configure Network Security Group

```bash
# Create network security rule for application port
az network nsg rule create \
  --resource-group $RESOURCE_GROUP \
  --nsg-name "${VM_NAME}NSG" \
  --name "Allow-HTTP-8080" \
  --priority 1001 \
  --protocol Tcp \
  --destination-port-ranges 8080 \
  --access Allow \
  --direction Inbound

echo "Network security rule created for port 8080"
```

## Step 7: Grant Key Vault Access to VM

```bash
# Assign Key Vault Secrets User role to VM's managed identity
az role assignment create \
  --assignee $PRINCIPAL_ID \
  --role "Key Vault Secrets User" \
  --scope "/subscriptions/$(az account show --query id -o tsv)/resourceGroups/$RESOURCE_GROUP/providers/Microsoft.KeyVault/vaults/$KEYVAULT_NAME"

echo "Key Vault access granted to VM managed identity"

# Verify role assignment
az role assignment list \
  --assignee $PRINCIPAL_ID \
  --scope "/subscriptions/$(az account show --query id -o tsv)/resourceGroups/$RESOURCE_GROUP/providers/Microsoft.KeyVault/vaults/$KEYVAULT_NAME" \
  --output table
```

## Step 8: VM Setup Script

Create a setup script for the VM:

```bash
cat > vm-setup.sh << 'EOF'
#!/bin/bash

# Update system
sudo apt update && sudo apt upgrade -y

# Install Java 8
sudo apt install -y openjdk-8-jdk

# Install Maven
sudo apt install -y maven

# Install Git
sudo apt install -y git

# Install Azure CLI
curl -sL https://aka.ms/InstallAzureCLIDeb | sudo bash

# Set JAVA_HOME
echo "export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64" >> ~/.bashrc
echo "export PATH=\$JAVA_HOME/bin:\$PATH" >> ~/.bashrc
source ~/.bashrc

# Verify installations
java -version
mvn -version
az --version

# Create application directory
sudo mkdir -p /opt/ftp-app
sudo chown $USER:$USER /opt/ftp-app

echo "VM setup completed successfully!"
EOF

chmod +x vm-setup.sh
```

## Step 9: Deploy Application to VM

```bash
# Copy setup script to VM
scp -i ~/.ssh/id_rsa vm-setup.sh $ADMIN_USERNAME@$VM_PUBLIC_IP:~/

# SSH to VM and run setup
ssh -i ~/.ssh/id_rsa $ADMIN_USERNAME@$VM_PUBLIC_IP "bash ~/vm-setup.sh"

# Create application deployment script
cat > deploy-app.sh << 'EOF'
#!/bin/bash

# Clone or copy your application code
cd /opt/ftp-app

# If using Git (replace with your repository)
# git clone https://github.com/your-org/ftp-app.git .

# For now, we'll create the application structure
mkdir -p src/main/java/com/echohealthcare/ftp
mkdir -p src/main/resources
mkdir -p src/test/java/com/echohealthcare/ftp

# Build and run application
mvn clean install -DskipTests
nohup mvn spring-boot:run > app.log 2>&1 &

echo "Application deployed and started!"
echo "Application log: tail -f /opt/ftp-app/app.log"
EOF

# Copy deployment script to VM
scp -i ~/.ssh/id_rsa deploy-app.sh $ADMIN_USERNAME@$VM_PUBLIC_IP:~/

# Note: You'll need to copy your actual application files to the VM
# This can be done via SCP, Git, or Azure DevOps pipelines
```

## Step 10: Application Service Script

Create a systemd service for the application:

```bash
cat > ftp-app.service << 'EOF'
[Unit]
Description=FTP Application with Azure Key Vault
After=network.target

[Service]
Type=simple
User=azureuser
WorkingDirectory=/opt/ftp-app
ExecStart=/usr/bin/mvn spring-boot:run
Restart=on-failure
RestartSec=10
Environment=JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64

[Install]
WantedBy=multi-user.target
EOF

# Copy service file to VM
scp -i ~/.ssh/id_rsa ftp-app.service $ADMIN_USERNAME@$VM_PUBLIC_IP:~/

# Install and start service
ssh -i ~/.ssh/id_rsa $ADMIN_USERNAME@$VM_PUBLIC_IP << 'COMMANDS'
sudo mv ~/ftp-app.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable ftp-app
sudo systemctl start ftp-app
sudo systemctl status ftp-app
COMMANDS
```

## Step 11: Testing and Debugging Commands

### Check Application Status
```bash
# SSH to VM
ssh -i ~/.ssh/id_rsa $ADMIN_USERNAME@$VM_PUBLIC_IP

# Check application logs
sudo journalctl -u ftp-app -f

# Check application process
ps aux | grep java

# Check port 8080
sudo netstat -tlnp | grep 8080
```

### Test Key Vault Access from VM
```bash
# SSH to VM and test Azure authentication
ssh -i ~/.ssh/id_rsa $ADMIN_USERNAME@$VM_PUBLIC_IP

# Login using managed identity
az login --identity

# Test Key Vault access
az keyvault secret show --vault-name echohelthcate-akv --name ftp-username
```

## Step 12: API Testing with cURL

### Health Check
```bash
# Test application health
curl -X GET http://$VM_PUBLIC_IP:8080/api/ftp/health

# Pretty print JSON response
curl -X GET http://$VM_PUBLIC_IP:8080/api/ftp/health | jq .
```

### Configuration Check
```bash
# Get application configuration
curl -X GET http://$VM_PUBLIC_IP:8080/api/ftp/config | jq .
```

### Test FTP Connection
```bash
# Test FTP connection
curl -X POST http://$VM_PUBLIC_IP:8080/api/ftp/test-connection | jq .
```

### File Operations
```bash
# Upload file
curl -X POST "http://$VM_PUBLIC_IP:8080/api/ftp/upload" \
  -d "localPath=/tmp/test.txt&remotePath=/remote/test.txt" \
  -H "Content-Type: application/x-www-form-urlencoded" | jq .

# Download file
curl -X POST "http://$VM_PUBLIC_IP:8080/api/ftp/download" \
  -d "remotePath=/remote/test.txt&localPath=/tmp/downloaded.txt" \
  -H "Content-Type: application/x-www-form-urlencoded" | jq .
```

### Get Individual Secrets
```bash
# Get specific secrets from Key Vault
curl -X GET http://$VM_PUBLIC_IP:8080/api/ftp/secret/ftp-username | jq .
curl -X GET http://$VM_PUBLIC_IP:8080/api/ftp/secret/api-key | jq .
curl -X GET http://$VM_PUBLIC_IP:8080/api/ftp/secret/email-password | jq .
```

## Step 13: Key Vault Client for External Access

Create a simple client to access Key Vault secrets:

```bash
cat > keyvault-client.sh << 'EOF'
#!/bin/bash

KEYVAULT_NAME="echohelthcate-akv"

# Function to get secret
get_secret() {
    local secret_name=$1
    if [ -z "$secret_name" ]; then
        echo "Usage: get_secret <secret-name>"
        return 1
    fi
    
    echo "Retrieving secret: $secret_name"
    az keyvault secret show --vault-name $KEYVAULT_NAME --name $secret_name --query "value" -o tsv
}

# Function to list all secrets
list_secrets() {
    echo "Available secrets in $KEYVAULT_NAME:"
    az keyvault secret list --vault-name $KEYVAULT_NAME --query "[].name" -o tsv
}

# Function to set secret
set_secret() {
    local secret_name=$1
    local secret_value=$2
    
    if [ -z "$secret_name" ] || [ -z "$secret_value" ]; then
        echo "Usage: set_secret <secret-name> <secret-value>"
        return 1
    fi
    
    echo "Setting secret: $secret_name"
    az keyvault secret set --vault-name $KEYVAULT_NAME --name $secret_name --value "$secret_value"
}

# Main script logic
case "$1" in
    "get")
        get_secret "$2"
        ;;
    "list")
        list_secrets
        ;;
    "set")
        set_secret "$2" "$3"
        ;;
    *)
        echo "Key Vault Client Usage:"
        echo "  $0 get <secret-name>        - Get a secret value"
        echo "  $0 list                     - List all secret names"
        echo "  $0 set <secret-name> <value> - Set a secret value"
        echo ""
        echo "Examples:"
        echo "  $0 get ftp-username"
        echo "  $0 list"
        echo "  $0 set new-secret 'secret-value'"
        ;;
esac
EOF

chmod +x keyvault-client.sh

# Test the client
./keyvault-client.sh list
./keyvault-client.sh get ftp-username
```

## Step 14: Monitoring and Logging

### Application Monitoring Script
```bash
cat > monitor-app.sh << 'EOF'
#!/bin/bash

VM_IP="$1"
if [ -z "$VM_IP" ]; then
    echo "Usage: $0 <vm-ip-address>"
    exit 1
fi

echo "Monitoring FTP Application on $VM_IP"
echo "======================================"

while true; do
    echo -n "$(date): "
    
    # Check if application is responding
    if curl -s -o /dev/null -w "%{http_code}" http://$VM_IP:8080/api/ftp/health | grep -q "200"; then
        echo "✅ Application is running"
    else
        echo "❌ Application is not responding"
    fi
    
    sleep 30
done
EOF

chmod +x monitor-app.sh

# Run monitoring
# ./monitor-app.sh $VM_PUBLIC_IP
```

## Step 15: Complete Test Suite

```bash
cat > test-suite.sh << 'EOF'
#!/bin/bash

VM_IP="$1"
if [ -z "$VM_IP" ]; then
    echo "Usage: $0 <vm-ip-address>"
    exit 1
fi

BASE_URL="http://$VM_IP:8080"

echo "Running Complete Test Suite for FTP Application"
echo "==============================================="

# Test 1: Health Check
echo "Test 1: Health Check"
curl -s "$BASE_URL/api/ftp/health" | jq .
echo ""

# Test 2: Configuration
echo "Test 2: Configuration Check"
curl -s "$BASE_URL/api/ftp/config" | jq .
echo ""

# Test 3: FTP Connection Test
echo "Test 3: FTP Connection Test"
curl -s -X POST "$BASE_URL/api/ftp/test-connection" | jq .
echo ""

# Test 4: Get Multiple Secrets
echo "Test 4: Key Vault Secrets"
secrets=("ftp-username" "api-key" "email-password" "jwt-secret")

for secret in "${secrets[@]}"; do
    echo "Getting secret: $secret"
    curl -s "$BASE_URL/api/ftp/secret/$secret" | jq .
    echo ""
done

echo "Test suite completed!"
EOF

chmod +x test-suite.sh

# Run complete test suite
# ./test-suite.sh $VM_PUBLIC_IP
```

## Troubleshooting

### Common Issues and Solutions

1. **Key Vault Access Denied**
   ```bash
   # Check managed identity assignment
   az role assignment list --assignee $PRINCIPAL_ID --output table
   
   # Re-assign role if needed
   az role assignment create --assignee $PRINCIPAL_ID --role "Key Vault Secrets User" --scope "/subscriptions/$(az account show --query id -o tsv)/resourceGroups/$RESOURCE_GROUP/providers/Microsoft.KeyVault/vaults/$KEYVAULT_NAME"
   ```

2. **Application Not Starting**
   ```bash
   # Check application logs
   ssh -i ~/.ssh/id_rsa $ADMIN_USERNAME@$VM_PUBLIC_IP "sudo journalctl -u ftp-app -n 50"
   
   # Check Java version
   ssh -i ~/.ssh/id_rsa $ADMIN_USERNAME@$VM_PUBLIC_IP "java -version"
   ```

3. **Port 8080 Not Accessible**
   ```bash
   # Check NSG rules
   az network nsg rule list --resource-group $RESOURCE_GROUP --nsg-name "${VM_NAME}NSG" --output table
   
   # Check if application is listening
   ssh -i ~/.ssh/id_rsa $ADMIN_USERNAME@$VM_PUBLIC_IP "sudo netstat -tlnp | grep 8080"
   ```

## Cleanup Resources

```bash
# Delete the entire resource group (this will delete all resources)
az group delete --name $RESOURCE_GROUP --yes --no-wait

echo "All resources deleted"
```

## Security Best Practices

1. **Network Security**: Restrict inbound rules to specific IP ranges
2. **Key Vault Access**: Use least privilege principle for role assignments
3. **VM Security**: Keep the VM updated and use strong SSH keys
4. **Application Security**: Use HTTPS in production and secure application endpoints
5. **Monitoring**: Enable Azure Monitor and Log Analytics for comprehensive monitoring

## Production Considerations

1. **Load Balancer**: Use Azure Load Balancer for high availability
2. **Auto Scaling**: Implement VM Scale Sets for automatic scaling
3. **Backup**: Configure regular backups for the VM and Key Vault
4. **SSL/TLS**: Configure SSL certificates for secure communication
5. **CI/CD**: Set up Azure DevOps or GitHub Actions for automated deployment